import maleAvatar from '../assets/avatar-male.png'
import femaleAvatar from '../assets/avatar-female.png'

const AVATARS = {
  male: maleAvatar,
  female: femaleAvatar
}

export function avatarFor(gender) {
  return AVATARS[gender] || ''
}
